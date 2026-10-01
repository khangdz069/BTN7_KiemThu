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
    public class Bai08
    {
        public TestContext TestContext { get; set; }

        [TestMethod]
        [DeploymentItem("data_csv\\Bai08.csv")]
        [DataSource("Microsoft.VisualStudio.TestTools.DataSource.CSV", "|DataDirectory|\\data_csv\\Bai08.csv", "Bai08#csv", DataAccessMethod.Sequential)]
        public void HuyChuoi()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            string s = TestContext.DataRow["s"].ToString();
            int n = Convert.ToInt32(TestContext.DataRow["n"]);
            int p = Convert.ToInt32(TestContext.DataRow["p"]);
            string exp = TestContext.DataRow["expected"].ToString();

            string act = m.HuyChuoi(s, n, p);

            Assert.AreEqual(exp, act);
        }
    }
}