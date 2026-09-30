using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace N7_btn
{
    [TestClass]
    public class Bai06_TinhTienDienTests_DDT
    {
        public TestContext TestContext { get; set; }

        [TestMethod]
        [DeploymentItem("data_csv\\Bai06_TinhTienDien_data.csv")]
        [DataSource(
            "Microsoft.VisualStudio.TestTools.DataSource.CSV",
            "|DataDirectory|\\data_csv\\Bai06_TinhTienDien_data.csv",
            "Bai06_TinhTienDien_data#csv", 
            DataAccessMethod.Sequential)]
        public void TinhTienDien_DataDriven()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            int c = Convert.ToInt32(TestContext.DataRow[0]);
            int m_val = Convert.ToInt32(TestContext.DataRow[1]);
            double exp = Convert.ToDouble(
                TestContext.DataRow[2],
                CultureInfo.InvariantCulture);

            double act = m.TinhTienDien(c, m_val);

            Assert.AreEqual(exp, act, 0.01);
        }
    }
}
